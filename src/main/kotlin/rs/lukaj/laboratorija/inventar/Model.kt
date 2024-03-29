package rs.lukaj.laboratorija.inventar

import java.time.LocalDate
import javax.persistence.*

@Entity
@Table(name="items")
class Item(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Int,
        var ime : String,
        var dobavljac: String,
        var brPartije : Int,
        var brStavke : Int,
        var cena : Double,
        @OneToMany(mappedBy = "item")
        var inventory : List<InventoryItem>
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Item

                if (brPartije != other.brPartije) return false
                if (brStavke != other.brStavke) return false

                return true
        }

        override fun hashCode(): Int {
                var result = brPartije
                result = 31 * result + brStavke
                return result
        }
}

@Entity
@Table(name = "inventories", uniqueConstraints = [UniqueConstraint(name = "inventories_ime_uindex", columnNames = ["ime"])])
class Inventory(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Int,
        var ime : String,
        @OneToMany(mappedBy = "inventory")
        var items : List<InventoryItem>,
        var sortOrder : Int,
        var modifiable : Boolean
)

@Entity
@Table(name = "inventory_items")
class InventoryItem(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Long,
        @ManyToOne
        var inventory : Inventory,
        @ManyToOne
        var item : Item,
        var kolicina : Double,
        @OneToMany(mappedBy = "item")
        var changes : List<Change>
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as InventoryItem

                if (id != other.id) return false

                return true
        }

        override fun hashCode(): Int {
                return id.hashCode()
        }
}

@Entity
@Table(name = "changes")
class Change(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Long,
        @ManyToOne
        var item : InventoryItem,
        var amount : Double,
        var date : LocalDate
)

@Entity
@Table(name = "sets")
class Set(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Int,
        @ManyToOne
        var item : Item,
        var name : String
)