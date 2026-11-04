package com.mirkwood.rutinify.data.system

import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.ZoneId
import javax.inject.Inject

class SystemZoneProvider @Inject constructor() : ZoneProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
