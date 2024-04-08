package com.example.k2022_03_09_radio

data class VideoStation(override val name: String, override val url: String, override val imageResource: Int): Station(name, url, imageResource)
