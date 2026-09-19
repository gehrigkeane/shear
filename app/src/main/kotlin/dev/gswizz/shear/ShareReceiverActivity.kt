/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Receives shared text and relays it to the native Sharesheet.
 *
 * Today this is a pass-through: the text is forwarded unchanged and Shear excludes itself from the chooser so a user
 * cannot loop. URL cleaning slots in between receipt and relay once the rules engine lands.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (text != null) {
            val relay = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            val self = ComponentName(this, ShareReceiverActivity::class.java)
            val chooser = Intent.createChooser(relay, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(self))
            startActivity(chooser)
        }
        finish()
    }
}
