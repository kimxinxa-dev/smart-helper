package com.smarthelper.app

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** 상태 표시줄·내비게이션 바·키보드에 화면이 가려지지 않게 여백을 준다. */
fun View.padForSystemBars() = setOnApplyWindowInsetsListener { v, insets ->
    if (Build.VERSION.SDK_INT >= 30) {
        val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(b.left, b.top, b.right, b.bottom)
    } else {
        @Suppress("DEPRECATION")
        v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
    }
    insets
}
