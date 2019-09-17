package se.emilsjolander.stickylistheaders

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * add expand/collapse functions like ExpandableListView
 * @author lsjwzh
 */
class ExpandableStickyListHeadersListView : StickyListHeadersListView {

    private lateinit var mExpandableStickyListHeadersAdapter: ExpandableStickyListHeadersAdapter


    private var mDefaultAnimExecutor: IAnimationExecutor? = object : IAnimationExecutor {
        override fun executeAnim(target: View, animType: Int) {
            if (animType == ANIMATION_EXPAND) {
                target.visibility = View.VISIBLE
            } else if (animType == ANIMATION_COLLAPSE) {
                target.visibility = View.GONE
            }
        }
    }

    interface IAnimationExecutor {
        fun executeAnim(target: View, animType: Int)
    }


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun getAdapter(): ExpandableStickyListHeadersAdapter? {
        return mExpandableStickyListHeadersAdapter
    }

    override fun setAdapter(adapter: StickyListHeadersAdapter) {
        mExpandableStickyListHeadersAdapter = ExpandableStickyListHeadersAdapter(adapter)
        super.setAdapter(mExpandableStickyListHeadersAdapter)
    }

    fun findViewByItemId(itemId: Long): View {
        return mExpandableStickyListHeadersAdapter.findViewByItemId(itemId)
    }

    fun findItemIdByView(view: View): Long {
        return mExpandableStickyListHeadersAdapter.findItemIdByView(view)
    }

    fun expand(headerId: Long) {
        if (!mExpandableStickyListHeadersAdapter.isHeaderCollapsed(headerId)) {
            return
        }
        mExpandableStickyListHeadersAdapter.expand(headerId)
        //find and expand views in group
        val itemViews =
            mExpandableStickyListHeadersAdapter.getItemViewsByHeaderId(headerId)
        for (view in itemViews) {
            animateView(view, ANIMATION_EXPAND)
        }
    }

    fun collapse(headerId: Long) {
        if (mExpandableStickyListHeadersAdapter.isHeaderCollapsed(headerId)) {
            return
        }
        mExpandableStickyListHeadersAdapter.collapse(headerId)
        //find and hide views with the same header
        val itemViews =
            mExpandableStickyListHeadersAdapter.getItemViewsByHeaderId(headerId)
        for (view in itemViews) {
            animateView(view, ANIMATION_COLLAPSE)
        }
    }

    fun isHeaderCollapsed(headerId: Long): Boolean {
        return mExpandableStickyListHeadersAdapter.isHeaderCollapsed(headerId)
    }

    fun setAnimExecutor(animExecutor: IAnimationExecutor) {
        this.mDefaultAnimExecutor = animExecutor
    }

    /**
     * Performs either COLLAPSE or EXPAND animation on the target view
     *
     * @param target the view to animate
     * @param type   the animation type, either ExpandCollapseAnimation.COLLAPSE
     * or ExpandCollapseAnimation.EXPAND
     */
    private fun animateView(target: View, type: Int) {
        if (ANIMATION_EXPAND == type && target.visibility == View.VISIBLE) {
            return
        }
        if (ANIMATION_COLLAPSE == type && target.visibility != View.VISIBLE) {
            return
        }
        if (mDefaultAnimExecutor != null) {
            mDefaultAnimExecutor!!.executeAnim(target, type)
        }

    }

    companion object {

        const val ANIMATION_COLLAPSE = 1
        const val ANIMATION_EXPAND = 0
    }

}
