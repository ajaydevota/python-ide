package com.sarvam.pythonide

import android.content.Context
import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object ProotEnv {

    fun nativeDir(context: Context) = File(context.applicationInfo.nativeLibraryDir)
    fun prootBin(context: Context) = File(nativeDir(context), "libproot.so")
    fun loaderBin(context: Context) = File(nativeDir(context), "libproot_loader.so")
    fun rootfs(context: Context) = File(context.filesDir, "rootfs")
    fun tmpDir(context: Context) = File(context.filesDir, "proot-tmp").apply { mkdirs() }

    fun abi(): String {
        val a = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (a.contains("x86_64")) "x86_64" else "arm64-v8a"
    }

    private fun openRootfsAsset(context: Context): InputStream? {
        val names = listOf(
            "proot/alpine-aarch64.rootfs", "proot/alpine-x86_64.rootfs",
            "proot/alpine-aarch64.tar", "proot/alpine-x86_64.tar",
            "proot/alpine-aarch64.tar.gz", "proot/alpine-x86_64.tar.gz"
        )
        for (n in names) {
            try {
                return context.assets.open(n)
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    private fun asTar(rawIn: InputStream): TarArchiveInputStream {
        val buffered = BufferedInputStream(rawIn, 1 shl 16)
        buffered.mark(4)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val isGzip = (b0 == 0x1f && b1 == 0x8b)
        return if (isGzip) TarArchiveInputStream(GzipCompressorInputStream(buffered))
        else TarArchiveInputStream(buffered)
    }

    private fun chmodX(f: File) {
        try {
            Os.chmod(f.absolutePath, 493) // 0755
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    fun ensureRootfs(context: Context): String {
        val rf = rootfs(context)
        val marker = File(rf, ".installed")
        if (marker.exists() && File(rf, "bin/busybox").exists()) return "rootfs pehle se ready hai"

        val raw = openRootfsAsset(context) ?: return "ERROR: rootfs asset nahi mila"

        rf.deleteRecursively()
        rf.mkdirs()
        var count = 0
        var symlinks = 0
        raw.use { r ->
            asTar(r).use { tar ->
                var e = tar.nextEntry
                while (e != null) {
                    var name = e.name
                    while (name.startsWith("./")) name = name.substring(2)
                    name = name.trimEnd('/')
                    if (name.isEmpty() || name.contains("..")) {
                        e = tar.nextEntry
                        continue
                    }
                    val out = File(rf, name)
                    when {
                        e.isDirectory -> {
                            out.mkdirs()
                            chmodX(out)
                        }
                        e.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            try {
                                if (out.exists()) out.delete()
                                Os.symlink(e.linkName, out.absolutePath)
                                symlinks++
                            } catch (ignored: Exception) {
                                try {
                                    val t = File(rf, e.linkName.removePrefix("/"))
                                    if (t.exists()) t.copyTo(out, overwrite = true)
                                } catch (ignored2: Exception) {
                                }
                            }
                        }
                        else -> {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { fos -> tar.copyTo(fos) }
                            // har file ko executable banao (rootfs ke liye safe)
                            chmodX(out)
                        }
                    }
                    count++
                    e = tar.nextEntry
                }
            }
        }
        marker.writeText("ok")
        val busybox = File(rf, "bin/busybox").exists()
        val sh = File(rf, "bin/sh").exists()
        val env = File(rf, "usr/bin/env").exists()
        return "rootfs install: " + count + " entries, " + symlinks + " symlinks" +
                " | busybox=" + busybox + " sh=" + sh + " env=" + env
    }

    fun run(context: Context, cmd: String, timeoutSec: Long = 180): String {
        val setup = ensureRootfs(context)
        val proot = prootBin(context)
        val loader = loaderBin(context)
        if (!proot.exists() || !loader.exists()) {
            return "ERROR: proot binaries nahi mile (" + proot.absolutePath + ")"
        }
        val rf = rootfs(context)
        val pb = ProcessBuilder(
            proot.absolutePath, "--link2symlink", "-0",
            "-r", rf.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-w", "/root",
            "/bin/sh", "-c", cmd
        )
        pb.environment()["PROOT_LOADER"] = loader.absolutePath
        pb.environment()["PROOT_TMP_DIR"] = tmpDir(context).absolutePath
        pb.environment()["PROOT_NO_SECCOMP"] = "1"
        pb.environment()["PROOT_VERBOSE"] = "0"
        pb.redirectErrorStream(true)
        return try {
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) {
                p.destroyForcibly()
                "[setup] " + setup + "\n" + out + "\n[timeout " + timeoutSec + "s]"
            } else {
                out
            }
        } catch (e: Exception) {
            "ERROR: " + e.message + "\n[setup] " + setup
        }
    }
}
