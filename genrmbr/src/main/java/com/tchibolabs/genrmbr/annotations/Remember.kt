package com.tchibolabs.genrmbr.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Remember(val injector: String = "")