package com.smarthelper.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 빠른 설정 패널(화면 위에서 쓸어내리면 나오는 곳)의 "QR 검사" 버튼.
 * 어느 화면에서든 누르면 스마트 헬퍼가 열리며 QR 스캐너가 바로 뜬다.
 * 팀원 @승민의 「스미싱 가드」 QrTileService 를 스마트 헬퍼에 맞게 옮겼다.
 */
class QrTileService : TileService() {

    override fun onStartListening() {
        // 켜고 끄는 스위치가 아니라 누르는 버튼이므로 늘 꺼진 모양으로 둔다
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::openScanner) else openScanner()
    }

    private fun openScanner() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SCAN_QR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
