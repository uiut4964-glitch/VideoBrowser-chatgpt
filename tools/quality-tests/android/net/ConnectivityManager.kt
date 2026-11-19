package android.net
class NetworkCapabilities {
    fun hasTransport(value: Int): Boolean = false
    companion object { const val TRANSPORT_WIFI = 1; const val TRANSPORT_ETHERNET = 2 }
}
class ConnectivityManager {
    val activeNetwork: Any? = null
    fun getNetworkCapabilities(network: Any?): NetworkCapabilities? = null
}
