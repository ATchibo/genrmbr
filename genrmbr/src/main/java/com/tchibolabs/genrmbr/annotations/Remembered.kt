package com.tchibolabs.genrmbr.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Remembered(val injector: String = "")