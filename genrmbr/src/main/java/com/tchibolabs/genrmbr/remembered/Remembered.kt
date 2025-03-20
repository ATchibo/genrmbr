package com.tchibolabs.genrmbr.remembered

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Remembered(val injector: String = "")