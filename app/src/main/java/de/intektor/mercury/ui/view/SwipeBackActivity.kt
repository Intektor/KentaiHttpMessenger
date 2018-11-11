package de.intektor.mercury.ui.view

import androidx.appcompat.app.AppCompatActivity


/**
 * https://github.com/liuguangqiang/SwipeBack/blob/master/library/src/main/java/com/liuguangqiang/swipeback/SwipeBackActivity.java
 * @author Intektor
 */
abstract class SwipeBackActivity : AppCompatActivity(), SwipeBackLayout.SwipeBackListener {

    abstract val swipeBackLayout: SwipeBackLayout

    fun setEnableSwipe(enableSwipe: Boolean) {
        swipeBackLayout.setEnablePullToBack(enableSwipe)
    }

    fun setDragEdge(dragEdge: SwipeBackLayout.DragEdge) {
        swipeBackLayout.setDragEdge(dragEdge)
    }

    fun setDragDirectMode(dragDirectMode: SwipeBackLayout.DragDirectMode) {
        swipeBackLayout.setDragDirectMode(dragDirectMode)
    }

}