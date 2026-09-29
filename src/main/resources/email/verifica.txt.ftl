<#assign validity = (hours == 1)?then("1 ora", hours?c + " ore")>
<#assign heading = name???then("Ciao " + name + ", ti diamo il benvenuto!", "Ti diamo il benvenuto!")>
${heading}

Grazie per l'iscrizione al sito di ABC Musical Company. Per completarla, conferma che questo indirizzo email è tuo aprendo questo link:

${link}

Il link vale ${validity}. Se non hai chiesto tu l'iscrizione, ignora questa email: senza conferma l'account non viene attivato.

<#include "footer.txt.ftl">
