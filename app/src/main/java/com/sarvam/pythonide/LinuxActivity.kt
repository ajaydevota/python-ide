package com.sarvam.pythonide

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinuxActivity : AppCompatActivity() {

    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_linux)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }
        val input = findViewById<EditText>(R.id.cmdInput)

        log("Linux (proot + Alpine) — experimental.")
        log("Pehli baar rootfs setup hoga (thoda time lagega).")
        log("")

        fun chip(id: Int, cmd: String) {
            findViewById<Button>(id).setOnClickListener { input.setText(cmd) }
        }
        chip(R.id.chipUname, "uname -a")
        chip(R.id.chipOs, "cat /etc/os-release")
        chip(R.id.chipPython, "apk add python3 py3-pip")
        chip(R.id.chipNumpy, "apk add py3-numpy")

        findViewById<Button>(R.id.runCmdBtn).setOnClickListener {
            val c = input.text.toString().trim()
            if (c.isNotEmpty()) {
                runCmd(c)
                input.setText("")
            }
        }

        lifecycleScope.launch {
            val s = withContext(Dispatchers.Default) { ProotEnv.ensureRootfs(this@LinuxActivity) }
            log("[setup] " + s)
            log("")
        }
    }

    private fun runCmd(cmd: String) {
        log("$ " + cmd)
        log("chal raha hai…")
        lifecycleScope.launch {
            val out = withContext(Dispatchers.Default) {
                ProotEnv.run(this@LinuxActivity, cmd)
            }
            log(out)
            log("")
        }
    }

    private fun log(line: String) {
        logBuffer.append(line).append("\n")
        findViewById<TextView>(R.id.logView).text = logBuffer.toString()
    }
}
