<#--
  Email per reimpostare la password.
  Modello: subject, link, siteUrl, siteHost, logoUrl, hours (validita' del link),
  name (facoltativo), poster (facoltativo). Layout e stile stanno in layout.ftlh.
-->
<#import "layout.ftlh" as l>
<#assign validity = (hours == 1)?then("1 ora", hours?c + " ore")>
<#assign validityShort = (hours == 1)?then("un'ora", hours?c + " ore")>
<@l.page
    title=subject
    preheader="Hai chiesto di cambiare la password del sito ABC Musical Company. Il link vale " + validityShort + "."
    eyebrow="Accesso al sito"
    heading=name???then("Ciao " + name + ", ecco il link per la nuova password", "Ecco il link per la nuova password")
    buttonLabel="Scegli una nuova password"
    link=link
    note="Il link vale " + validity + " e si può usare una volta sola. Se non hai fatto tu la richiesta, ignora questa email: la tua password attuale resta valida.">
Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account sul sito di <strong>ABC Musical Company</strong>. Scegli la nuova password dal tasto qui sotto:<#t>
</@l.page>
