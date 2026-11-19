package com.martensening.my_audio.data

import android.content.Context

/** The app's repository in its private storage. */
fun ProjectRepository.Companion.get(context: Context): ProjectRepository =
    forRoot(context.applicationContext.filesDir)
