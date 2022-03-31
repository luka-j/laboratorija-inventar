const table = document.getElementById('inputTable')
const firedEvents = new Set()
const errors = new Set()
const errorType = new Map()
const btn = document.getElementById('submit-btn')

window.onbeforeunload = function () {
    if(firedEvents.size > 0) {
        return "Podaci nisu sačuvani. Napusti stranicu bez čuvanja?"
    } else {
        return false
    }
}

const row = document.getElementById('row-0')
const brPartije = row.querySelector('input[name="brPartije"]')
const brStavke = row.querySelector('input[name="brStavke"]')
const ime = row.querySelector('input[name="ime"]')
const dobavljac = row.querySelector('input[name="dobavljac"]')
const crossMark = row.querySelector('.errorMark')
brPartije.addEventListener('change', createListenerAddRow(0))
brStavke.addEventListener('change', createListenerAddRow(0))
ignoreWheel(brPartije)
ignoreWheel(brStavke)
addValidityListeners(brPartije, brStavke, ime, dobavljac, crossMark)


btn.addEventListener('click', function () {
    disableButton()
    try {
        const dataRows = document.getElementsByClassName('data-row')
        const data = []
        for (let i = 0; i < dataRows.length; i++) {
            const row = dataRows[i]
            const partija = row.querySelector('input[name="brPartije"]').value
            const stavka = row.querySelector('input[name="brStavke"]').value
            const ime = row.querySelector('input[name="ime"]').value
            const dobavljac = row.querySelector('input[name="dobavljac"]').value
            if (partija == '' && stavka == '' && ime == '' && dobavljac == '') continue;
            if (partija == '' || stavka == '' || ime == '') {
                alert("Nisu sva polja ispunjena! Partija: " + partija + ", stavka: " + stavka + ", ime: " + ime + ", dobavljač: " + dobavljac)
                enableButton()
                return
            }
            data.push({
                brPartije: Number(partija),
                brStavke: Number(stavka),
                ime: ime,
                dobavljac: dobavljac
            })
        }
        fetch('/api/item', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        }).then(data => {
            if (data.status === 200) {
                window.onbeforeunload = undefined
                window.location.href = '/'
            } else {
                window.alert('Došlo je do greške. Status ' + data.status)
                console.log(data)
                enableButton()
            }
        }).catch(err => {
            window.alert('Došlo je do greške u komunikaciji')
            console.error(err)
            enableButton()
        })
    } catch (e) {
        enableButton()
        alert('Došlo je do nepredviđene greške :/\n' + e)
        throw e
    }
})

function createListenerAddRow(rowNumber) {
    return function() {
        if(!firedEvents.has(rowNumber)) {
            firedEvents.add(rowNumber)
            const nextRow = document.createElement('tr')
            nextRow.classList.add('data-row')
            const nextBrPartije = document.createElement('td')
            const nextBrPartijeInput = document.createElement('input')
            nextBrPartijeInput.type = 'number'
            nextBrPartijeInput.name = 'brPartije'
            nextBrPartijeInput.addEventListener('change', createListenerAddRow(rowNumber+1))
            ignoreWheel(nextBrPartijeInput)
            nextBrPartije.appendChild(nextBrPartijeInput)
            nextRow.appendChild(nextBrPartije)

            const nextBrStavke = document.createElement('td')
            const nextBrStavkeInput = document.createElement('input')
            nextBrStavkeInput.type = 'number'
            nextBrStavkeInput.name = 'brStavke'
            nextBrStavkeInput.addEventListener('change', createListenerAddRow(rowNumber+1))
            ignoreWheel(nextBrStavkeInput)
            nextBrStavke.appendChild(nextBrStavkeInput)
            nextRow.appendChild(nextBrStavke)

            const nextIme = document.createElement('td')
            const nextImeInput = document.createElement('input')
            nextImeInput.type = 'text'
            nextImeInput.name = 'ime'
            nextIme.appendChild(nextImeInput)
            nextRow.appendChild(nextIme)

            const nextDobavljac = document.createElement('td')
            const nextDobavljacInput = document.createElement('input')
            nextDobavljacInput.type = 'text'
            nextDobavljacInput.name = 'dobavljac'
            nextDobavljac.appendChild(nextDobavljacInput)
            nextRow.appendChild(nextDobavljac)

            const nextCrossMark = document.createElement('td')
            const nextCrossMarkI = document.createElement('i')
            nextCrossMarkI.className = 'fas fa-times-circle errorMark'
            nextCrossMarkI.hidden = true
            nextCrossMark.appendChild(nextCrossMarkI)
            nextRow.append(nextCrossMark)

            addValidityListeners(nextBrPartijeInput, nextBrStavkeInput, nextImeInput, nextDobavljacInput, nextCrossMarkI)

            table.appendChild(nextRow)
        }
    }
}

function addValidityListeners(brPartijeInput, brStavkeInput, imeInput, dobavljacInput, crossMark) {
    brPartijeInput.addEventListener('change', createListenerCheckValidity(true, brPartijeInput,
        brStavkeInput, imeInput, dobavljacInput, crossMark))
    brStavkeInput.addEventListener('change', createListenerCheckValidity(true, brPartijeInput,
        brStavkeInput, imeInput, dobavljacInput, crossMark))
    imeInput.addEventListener('change', createListenerCheckValidity(false, brPartijeInput,
        brStavkeInput, imeInput, dobavljacInput, crossMark))
    dobavljacInput.addEventListener('change', createListenerCheckValidity(false, brPartijeInput,
        brStavkeInput, imeInput, dobavljacInput, crossMark))
}

function createListenerCheckValidity(checkIdCollision, brPartije, brStavke, ime, dobavljac, crossMark) {
    return function () {
        if (brPartije.value == '' && brStavke.value == '' && ime.value == '' && dobavljac.value == '') {
            crossMark.hidden = true
            errors.delete(crossMark)
            if(errors.size === 0) enableButton()
            return
        }
        if (brPartije.value == '' || brStavke.value == '') {
            crossMark.hidden = false
            errors.add(crossMark)
            errorType.set(crossMark, "missing")
            disableButton()
            return
        }
        if(checkIdCollision) {
            fetch('/api/item/exists', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    brPartije: Number(brPartije.value),
                    brStavke: Number(brStavke.value)
                })
            }).then(data => {
                const isValid = data.status === 200;
                if(isValid) {
                    if(errors.has(crossMark) && errorType.get(crossMark) === "duplicate") {
                        errors.delete(crossMark)
                        errorType.delete(crossMark)
                        crossMark.hidden = true
                        if(errors.size === 0) enableButton()
                    }
                } else {
                    crossMark.hidden = false
                    errors.add(crossMark)
                    errorType.set(crossMark, "duplicate")
                    disableButton()
                }
            }).catch(err => {
                console.error('Error while checking id collision: ' + err)
            })
        } else if(errors.has(crossMark) && errorType.get(crossMark) === "duplicate") {
            disableButton()
            return
        }
        if (ime.value == '') {
            crossMark.hidden = false
            errors.add(crossMark)
            errorType.set(crossMark, "missing")
            disableButton()
            return
        }
        crossMark.hidden = true
        errors.delete(crossMark)
        if(errors.size === 0) enableButton()
    }
}

function ignoreWheel(input) {
    input.addEventListener('wheel', function () {input.blur()})
}