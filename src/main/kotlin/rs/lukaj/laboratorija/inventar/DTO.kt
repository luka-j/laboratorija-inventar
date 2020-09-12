package rs.lukaj.laboratorija.inventar

data class TransferItem(val brPartije: Int, val brStavke: Int, val amount: Double)

data class TransferRequest(val from: Int, val to: Int, val time: String, val items: Array<TransferItem>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransferRequest

        if (from != other.from) return false
        if (to != other.to) return false
        if (!items.contentEquals(other.items)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from
        result = 31 * result + to
        result = 31 * result + items.contentHashCode()
        return result
    }
}