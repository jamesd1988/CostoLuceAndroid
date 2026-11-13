# Read-only Enel invoice synchronization

The client uses the authenticated session already established by EnelConnector. No cookies, HTTP payloads, identifiers or PDFs are logged or exported. All remote requests are bounded and restricted to the existing HTTPS Enel host allowlist. PDF data is processed in memory by the on-device reader and discarded.

The non-public protocol was identified in independent integration source code:

- https://github.com/zenimarc/bollette-auto-downloader/blob/b0db782cbc6ba195da555bcccf81d4269d4f3a20/scraper/Enel.py (getCards; getArchivioBollette request/response field names; downloadFARO; legacy document endpoint; map.data base64 PDF envelope).
- https://github.com/subnetMusk/consumi_enel/blob/42d034b3ebf2257f0e54b5389e4cde3cf8e3f19a/scripts/fetch_bollette.py (archive POST form and parameters, 2025).

This implementation was written independently. These sources describe protocol facts, not an official stable API or proof that all current accounts support it. No source login/cookie persistence or logging behavior was reused. Live operation still requires the customer's successful Enel session. CI uses synthetic transport responses and in-memory test documents only.

1. GET getCards and select exactly the card whose pod matches the configured supply. Do not select the first contract or use a different account's documents.
2. Read required contract account, business partner and supply identifiers. Use an optional encrypted fiscal code or a fiscal-code field in that authenticated response; if unavailable, attempt the archive with an empty cf and expose a specific configuration hint on failure.
3. POST getArchivioBollette as URL-encoded pagamentiRequest JSON for that contract only. Select only the matching contoContrattuale group. Read Numerodocumento, Dataemissione and Importofattura, not the payment balance.
4. Process recent issued documents (16-month emission window, newest first, max 24 new PDFs per call, existing known matching totals skipped). Continue on subsequent refresh if the global deadline is nearly reached.
5. GET downloadFARO with the documented parameters; if unavailable, try the documented legacy download-fattura read. Accept only a bounded PDF or the documented map.data base64 envelope. Do not follow document URLs provided in JSON to arbitrary hosts.
6. Extract the original electricity total and consumption period, with separate total payable. Verify POD, normalized document number and total payable against the metadata. Reject unrecognized, ambiguous or mismatched documents.
7. Save verified values in the private per-supply invoice store. Archive or document errors never substitute estimates for issued bills, clear previous invoices or overwrite manual tariffs. Current-period estimates and electricity/TV separation remain unchanged.

No payment or account-changing operations are performed. No invoice is shown as a successful import until it has been parsed, matched and saved; the result reports download failures and storage conflicts separately.
