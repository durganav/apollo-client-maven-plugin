package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.api.BigDecimal
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue

object LongTypeAdapter : CustomTypeAdapter<Long> {
    override fun encode(value: Long): CustomTypeValue<*> {
        return value.toString() as CustomTypeValue<String>
    }

    override fun decode(value: CustomTypeValue<*>): Long {
        return (value.value as BigDecimal).toLong()
    }
}
