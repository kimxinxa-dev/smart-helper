package com.smarthelper.testapp

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** 지우기 연습용 앱. 아무 기능이 없으니 마음 놓고 지워도 된다. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "🧩 연습용 퍼즐\n\n이 앱은 '앱 지우기' 연습용이에요.\n마음 놓고 지워 보세요."
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        })
    }
}
