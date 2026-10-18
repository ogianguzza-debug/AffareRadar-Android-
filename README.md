# AffareRadar Android 2.0

App Android per raccogliere candidati da marketplace, esaminarne le foto e mostrare opportunità documentate. Build firmata e prova automatica su Android 9 in `.github/workflows/build-apk.yml`.

## Funzionamento

- I servizi Supabase raccolgono annunci dalle fonti abilitate. L'app distingue candidati preliminari, oggetti con foto esaminate e opportunità che superano i filtri.
- `review-deals.yml` esegue ogni ora, al minuto 23, fino a quattro esami fotografici con Qwen3-VL 2B su Ollama nel runner GitHub pubblico standard. Non usa API AI a pagamento. Il server accetta risultati solo dall'identità OIDC di questo workflow sul branch `main`.
- Una notifica richiede foto esaminate, confidenza almeno 75, nessun rischio o dettaglio fotografico mancante, almeno tre comparabili distinti con link, margine prudente almeno 15.000 HUF e prezzo d'acquisto coerente con la valutazione. Le aste non sono trattate come prezzi di acquisto.
- Il calcolo usa ipotesi esplicite: 8% sull'acquisto, 1.800 HUF di costo fisso e 12% sulla rivendita. Questi importi richiedono verifica per ogni operazione.
- Il controllo Android è pianificato ogni 15 minuti: Android può ritardarlo. L'esame fotografico automatico non certifica l'autenticità.

## Accessi ancora necessari

Vinted restituisce HTTP 403 dallo scanner: il connettore segnala la protezione e applica una pausa, senza aggirarla. eBay/Etsy richiedono accesso API; Facebook/Vestiaire restano assistiti. I prezzi di vendita di Galéria Savaria sono nascosti nella consultazione pubblica verificata: i comparabili senza prezzo visibile non alimentano stime o avvisi. Nessuna password o sessione personale viene copiata. Le notifiche dei marketplace senza link al singolo annuncio restano da collegare; non vengono associate per somiglianza del titolo.

## Verifiche

`node tools/test_policy.ts` controlla i criteri degli avvisi; `node tools/test_parsers.ts` controlla prezzi fissi, aste e prezzi di vendita nascosti. Il job `smoke` installa l'APK firmato su Android 9, apre il radar, legge il feed reale e controlla gli arresti anomali. Gli screenshot sono negli artifact della build. Non sostituisce la verifica sul telefono, con schermo spento e risparmio energetico.

Le funzioni versionate sono in `supabase/functions`; gli interventi SQL mirati applicati durante la verifica sono in `supabase/ops`. Le tabelle condivise estranee ad AffareRadar non vanno modificate.
