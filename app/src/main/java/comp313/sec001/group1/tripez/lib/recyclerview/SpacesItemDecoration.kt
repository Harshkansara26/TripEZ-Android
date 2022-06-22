package comp313.sec001.group1.tripez.lib.recyclerview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration


class SpacesItemDecoration(
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int
) : ItemDecoration() {
    constructor(space: Int) : this(space,space,space,space)
    constructor(horizontal: Int, vertical: Int) : this(horizontal,vertical,horizontal,vertical)
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = left
        outRect.right = right
        outRect.bottom = bottom
        outRect.top = top
    }
}