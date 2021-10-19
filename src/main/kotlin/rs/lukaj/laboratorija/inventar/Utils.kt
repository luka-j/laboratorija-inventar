package rs.lukaj.laboratorija.inventar


class ItemWithMappedInventory(var id : Int, var ime : String, var dobavljac : String, var brPartije: Int,
                              var brStavke: Int, var cena: Double, var amounts: HashMap<String, Double>) {
    constructor(item: Item) :  this(item.id, item.ime, item.dobavljac, item.brPartije, item.brStavke, item.cena,
            HashMap()) {
        amounts = HashMap()
        for(inv in item.inventory) {
            amounts[inv.inventory.ime] = inv.kolicina
        }
    }
}

class NotFoundException(msg: String) : Exception(msg)

class BadRequestException(msg: String) : Exception(msg)

class ConflictException(msg: String) : Exception(msg)

class MutablePair<F, S>(var first: F, var second: S)