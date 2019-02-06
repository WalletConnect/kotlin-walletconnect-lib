package org.walletconnect.model

data class AccountsResponseInner(val accounts: List<String>, val approved: Boolean)
data class AccountsResponse(val data: AccountsResponseInner)