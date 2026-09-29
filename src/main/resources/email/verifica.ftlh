<#--
  Email di verifica dell'indirizzo, inviata dopo l'iscrizione.
  Modello: subject, link, siteUrl, siteHost, logoUrl, hours (validita' del link),
  name (facoltativo), poster (facoltativo). Layout e stile stanno in layout.ftlh.
-->
<#import "layout.ftlh" as l>
<#assign validity = (hours == 1)?then("1 ora", hours?c + " ore")>
<@l.page
    title=subject
    preheader="Un clic per confermare l'indirizzo email e completare l'iscrizione ad ABC Musical Company."
    eyebrow="Conferma l'iscrizione"
    heading=name???then("Ciao " + name + ", ti diamo il benvenuto!", "Ti diamo il benvenuto!")
    buttonLabel="Conferma la mia email"
    link=link
    note="Il link vale " + validity + ". Se non hai chiesto tu l'iscrizione, ignora questa email: senza conferma l'account non viene attivato.">
Grazie per l'iscrizione al sito di <strong>ABC Musical Company</strong>. Per completarla, conferma che questo indirizzo email &egrave; tuo:<#t>
</@l.page>
