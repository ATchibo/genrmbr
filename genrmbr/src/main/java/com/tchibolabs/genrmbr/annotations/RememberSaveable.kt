package com.tchibolabs.genrmbr.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RememberSaveable(val injector: String = "")