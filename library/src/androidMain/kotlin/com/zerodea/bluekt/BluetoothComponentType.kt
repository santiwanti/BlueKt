package com.zerodea.bluekt

sealed class ComponentType {
    data object Client : ComponentType()
    class Server(val name: String) : ComponentType()
}