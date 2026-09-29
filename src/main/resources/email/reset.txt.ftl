<#assign validity = (hours == 1)?then("1 ora", hours?c + " ore")>
<#assign heading = name???then("Ciao " + name + ", ecco il link per la nuova password", "Ecco il link per la nuova password")>
${heading}

Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account sul sito di ABC Musical Company. Scegli la nuova password da questo link:

${link}

Il link vale ${validity} e si può usare una volta sola. Se non hai fatto tu la richiesta, ignora questa email: la tua password attuale resta valida.

<#include "footer.txt.ftl">
