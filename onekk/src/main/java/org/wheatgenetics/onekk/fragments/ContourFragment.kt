package org.wheatgenetics.onekk.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.fragment_contour_list.*
import kotlinx.coroutines.*
import org.opencv.core.CvException
import org.wheatgenetics.imageprocess.DrawSelectedContour
import org.wheatgenetics.onekk.R
import org.wheatgenetics.onekk.adapters.ContourAdapter
import org.wheatgenetics.onekk.database.OnekkDatabase
import org.wheatgenetics.onekk.database.OnekkRepository
import org.wheatgenetics.onekk.database.models.ContourEntity
import org.wheatgenetics.onekk.database.viewmodels.ExperimentViewModel
import org.wheatgenetics.onekk.database.viewmodels.factory.OnekkViewModelFactory
import org.wheatgenetics.onekk.databinding.FragmentContourListBinding
import org.wheatgenetics.onekk.interfaces.ContourOnTouchListener
import org.wheatgenetics.utils.Dialogs
import kotlin.properties.Delegates

class ContourFragment : Fragment(), CoroutineScope by MainScope(), ContourOnTouchListener {

    private val db: OnekkDatabase by lazy {
        OnekkDatabase.getInstance(requireContext())
    }

    private val mPreferences by lazy {
        requireContext().getSharedPreferences(getString(R.string.onekk_preference_key), Context.MODE_PRIVATE)
    }

    private val sViewModel: ExperimentViewModel by viewModels {

        OnekkViewModelFactory(
                OnekkRepository.getInstance(db.dao(), db.coinDao()))

    }

    private var mSourceBitmap: String? = null
    private var aid by Delegates.notNull<Int>()
    private var mSortState: Boolean = true
    private var mSortMode: Int = 0
    private var mBinding: FragmentContourListBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        aid = requireArguments().getInt("analysis", -1)

        val contextThemeWrapper = ContextThemeWrapper(activity, R.style.AppTheme)

        val localInflater = inflater.cloneInContext(contextThemeWrapper)

        mBinding = DataBindingUtil.inflate(localInflater, R.layout.fragment_contour_list, null, false)

        setHasOptionsMenu(true)

        mBinding?.setupRecyclerView(aid)

        mBinding?.setupHeaderSortButtons()

        updateUi(aid)

        sViewModel.getSourceImage(aid).observeForever { uri ->

            mSourceBitmap = uri

            try {

                Glide.with(requireContext()).asBitmap().load(uri).fitCenter().into(imageView)
                //imageView?.setImageBitmap(BitmapFactory.decodeFile(mSourceBitmap))

            } catch (e: Exception) {
                e.printStackTrace()
            }

            mBinding?.imageView?.visibility = View.VISIBLE

            mBinding?.imageLoadingTextView?.visibility = View.GONE
        }

        mBinding?.submitButton?.text = getString(R.string.frag_contour_list_button_loading)

        sViewModel.contours(aid).observeForever { contours ->

            if (contours.isNotEmpty()) {

                val count = contours.filter { it.selected }.mapNotNull { it.contour?.count }.reduceRight { x, y ->  y + x }

                submitButton?.text = "${getString(R.string.frag_contour_list_total)} $count"

                mBinding?.setupButtons(count)
            }
        }

        return mBinding?.root
    }

    /**
     * Clicking on the headers sorts by the header name.
     * Elements are also sorted by the tool bar ascending/descending mode.
     */
    private fun FragmentContourListBinding.setupHeaderSortButtons() {

        contourHeader.areaTextView.setOnClickListener {
            mSortMode = 0
            updateUi(aid)
        }

        contourHeader.lengthTextView.setOnClickListener {
            mSortMode = 1
            updateUi(aid)
        }

        contourHeader.widthTextView.setOnClickListener {
            mSortMode = 2
            updateUi(aid)
        }

        contourHeader.countTextView.setOnClickListener {
            mSortMode = 3
            updateUi(aid)
        }
    }

    suspend fun updateImageView(x: Double, y: Double, cluster: Boolean, minAxis: Double, maxAxis: Double): Deferred<Bitmap> = withContext(Dispatchers.IO) {

        async {

            val bmp = BitmapFactory.decodeFile(mSourceBitmap)

            try {

                DrawSelectedContour().process(bmp, x, y, cluster, minAxis, maxAxis)

            } catch (e: CvException) {

                e.printStackTrace()

                bmp
            }
        }
    }

    private var mLastSelectedContourId = -1
    /**
     * Interface function that returns the cropped image around the chosen contour.
     * Uses a global variable to track if the same contour was clicked, if it was then show the
     * original image; otherwise, show the new contour region.
     */
    override fun onTouch(cid: Int, x: Double, y: Double, cluster: Boolean, minAxis: Double, maxAxis: Double) {

        launch {

            mLastSelectedContourId = when (mLastSelectedContourId) {

                cid -> {

                    mBinding?.imageView?.setImageBitmap(BitmapFactory.decodeFile(mSourceBitmap))

                    -1
                }
                else -> {

                    mBinding?.imageView?.setImageBitmap(
                            updateImageView(x, y, cluster, minAxis, maxAxis)
                                    .await())

                    cid
                }

            }
        }
    }

    private fun updateTotal() = with(requireActivity()) {

        sViewModel.contours(aid).observeForever { contours ->

            runOnUiThread {

                val count = contours.filter { it.selected }.mapNotNull { it.contour?.count }.reduceRight { x, y ->  y + x }

                mBinding?.submitButton?.setText(getString(R.string.frag_contour_list_total) + count)

            }
        }
    }

    override fun onChoiceSwapped(id: Int, selected: Boolean) {

        launch {

            sViewModel.switchSelectedContour(aid, id, selected)

            updateTotal()

            updateUi(aid)

        }
    }

    private fun FragmentContourListBinding.setupButtons(count: Int) {

        submitButton.setOnClickListener {

            sViewModel.updateAnalysisCount(aid, count)

            sViewModel.getAnalysis(aid).observe(viewLifecycleOwner, {

                when(mPreferences.getString(getString(R.string.onekk_preference_mode_key), "1")) {

                    "1", "2" -> findNavController().popBackStack()

                    else -> findNavController().navigate(ContourFragmentDirections.actionToScale(aid))
                }
            })
        }
    }

    private fun FragmentContourListBinding.setupRecyclerView(aid: Int) {

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        recyclerView?.adapter = ContourAdapter(this@ContourFragment)

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {

        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_contour_view, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.action_graph -> {

                findNavController().navigate(ContourFragmentDirections.actionToGraph(aid))

            }

            R.id.action_sort -> {

                item.setIcon(when (mSortState) {

                    //ascending order by area, switch to descending
                    true -> {

                        mSortState = false

                        R.drawable.ic_sort_variant

                    }

                    //descending to ascending
                    else -> {

                        mSortState = true

                        R.drawable.ic_sort_reverse_variant

                    }
                })

            }

            else -> return super.onOptionsItemSelected(item)
        }

        updateUi(aid)

        return true
    }

    private fun updateUi(aid: Int) {

        sViewModel.contours(aid).observeForever { data ->

            val singles = data.filter { it.contour?.count ?: 0 <= 1 }

            val clusters = data.filter { it.contour?.count ?: 0 > 1 }

            val contours = (singles + clusters)

            //uses the two different modes to sort (ascending/descending) vs (area/l/w/count)
            val sorted = sortByState(contours)

            requireActivity().runOnUiThread {
                (mBinding?.recyclerView?.adapter as? ContourAdapter)
                        ?.submitList(sorted)
            }
        }

        Handler().postDelayed({
            mBinding?.recyclerView?.scrollToPosition(0)
        }, 500)
    }

    private fun sortByState(contours: List<ContourEntity>): List<ContourEntity> {

        return when(mSortState) {

            //sort by ascending
            true -> sortByMode(true, contours)

            //sort by descending
            else -> sortByMode(false, contours)
        }

    }

    private fun sortByMode(ascending: Boolean, contours: List<ContourEntity>): List<ContourEntity> {

        return when (mSortMode) {

            0 -> {

                if (ascending) contours.sortedBy { it.contour?.area }
                else contours.sortedByDescending { it.contour?.area }

            }
            1 -> {

                if (ascending) contours.sortedBy { it.contour?.maxAxis }
                else contours.sortedByDescending { it.contour?.maxAxis }

            }
            2 -> {

                if (ascending) contours.sortedBy { it.contour?.minAxis }
                else contours.sortedByDescending { it.contour?.minAxis }

            }
            else -> {

                if (ascending) contours.sortedBy { it.contour?.count }
                else contours.sortedByDescending { it.contour?.count }

            }
        }
    }
}