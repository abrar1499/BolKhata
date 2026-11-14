package com.bolkhata.app

/** Changes to what is saved: corrections, delete, restore, rename and merge, accounts. */
object LocalEdit : LocalAgent.Reader {
    override fun read(s: LocalAgent.Said): LocalAgent.Reply? = null
}
