package utils

import Node

fun List<Node>.sortByLoad(): List<Node> {
    this.sortedBy {
        it.statistics?.penalty
    }
    return this
}

fun List<Node>.sortByPlayers(): List<Node> {
    this.sortedBy {
        it.players.size
    }
    return this
}