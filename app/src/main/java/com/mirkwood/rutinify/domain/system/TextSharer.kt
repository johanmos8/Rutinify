package com.mirkwood.rutinify.domain.system

/** Whether some app can receive shared text ("Abrir en IA", R-14.7, R-14.8). Implemented in `data/system`. */
interface TextSharer {

    fun canShareText(): Boolean
}
