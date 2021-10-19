function changeItem(id, field, newValue) {

    let check = document.getElementById("check-" + id)
    let cross = document.getElementById("cross-" + id)
    fetch('/api/item', {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: id,
            field: field,
            newValue: newValue
        })
    }).then(r => {
        if (r.status === 200) {
            check.hidden = false
            cross.hidden = true
        } else {
            check.hidden = true
            cross.hidden = false
        }
    })
        .catch(err => {
            window.alert('Došlo je do greške u komunikaciji')
            console.error(err)
        })
}


function deleteItem(id, brPartije, brStavke) {
    if(!window.confirm("Sigurno obrisati stavku " + brPartije + " / " + brStavke + "?")) return

    let check = document.getElementById("check-" + id)
    let cross = document.getElementById("cross-" + id)
    fetch('/api/item/' + id, {
        method: 'DELETE',
    }).then(r => {
        if (r.status === 200) {
            document.getElementById("row-" + id).remove()
        } else {
            check.hidden = true
            cross.hidden = false
        }
    })
        .catch(err => {
            window.alert('Došlo je do greške u komunikaciji')
            console.error(err)
        })
}