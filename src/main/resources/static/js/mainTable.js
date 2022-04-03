const searchPartije = document.getElementById("search-brPartije")
const searchStavke = document.getElementById("search-brStavke")

searchPartije.value = ''
searchStavke.value = ''

searchPartije.addEventListener('input', updateSearch)
searchStavke.addEventListener('input', updateSearch)

function updateSearch(e) {
    const rows = document.getElementsByClassName("item-row")
    for(const row of rows) {
        if(!row.id.startsWith(searchPartije.value.trim()) || !row.id.split("-", 2)[1].startsWith(searchStavke.value.trim())) {
            row.style.display = 'none';
        } else {
            row.style.display = 'table-row';
        }
    }
}