package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Joining this phone's data with Google Drive's: nothing lost, no entry twice, this phone's copy first. */
class DriveTest {
    private fun s(v: String) = JSONObject().put("t", "s").put("v", v)
    private fun entry(id: String, amount: Int) = JSONObject().put("id", id).put("amount", amount)
    private fun backup(vararg data: Pair<String, JSONObject>) =
        JSONObject().put("app", "Smart Expense").put("data", JSONObject().apply { data.forEach { (k, v) -> put(k, v) } }).toString()

    @Test
    fun entriesFromBothPhonesAreJoinedOnce() {
        val phone = backup("entries" to s(JSONArray().put(entry("a", 100)).put(entry("b", 250)).toString()))
        val drive = backup("entries" to s(JSONArray().put(entry("b", 999)).put(entry("c", 40)).toString()))
        val out = JSONObject(DriveData.merge(phone, drive))
        val list = JSONArray(out.getJSONObject("data").getJSONObject("entries").getString("v"))
        assertEquals(listOf("a", "b", "c"), (0 until list.length()).map { list.getJSONObject(it).getString("id") })
        // The same entry on both: this phone's copy stays.
        assertEquals(250, list.getJSONObject(1).getInt("amount"))
        assertEquals(3, out.getInt("entries"))
    }

    @Test
    fun businessesAndSettingsAreKept() {
        val phone = backup("businessNames" to s("""{"b1":"Shop"}"""), "currency" to s("PKR"))
        val drive = backup(
            "businessNames" to s("""{"b2":"Stitching"}"""), "currency" to s("SAR"),
            "entries@b2" to s(JSONArray().put(entry("x", 5)).toString())
        )
        val data = JSONObject(DriveData.merge(phone, drive)).getJSONObject("data")
        val names = JSONObject(data.getJSONObject("businessNames").getString("v"))
        assertEquals(setOf("b1", "b2"), names.keys().asSequence().toSet())
        assertEquals("PKR", data.getJSONObject("currency").getString("v"))
        assertEquals(1, JSONArray(data.getJSONObject("entries@b2").getString("v")).length())
    }
}
