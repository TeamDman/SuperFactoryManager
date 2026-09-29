# SFM server config comparison from retained release witnesses

The retained exact-loader server witnesses have matching `sfm-server.toml` bytes in all ten official/projected pairs. Four targets generated the file independently in separate boot roots. Six targets inherited it from the same official seed world, so their equality is a weaker check.

The read-only [comparison helper](../../scripts/release-server-witness/Compare-ReleaseServerConfig.ps1) accepts one explicit witness root and expected target. It checks the four-boot `PASS` report and exact-loader/JAR identities. It then hashes the SFM server config from official seed, official control, projected candidate and official reverse. It fails if any file is missing or any bytes differ. Its JSON output contains no local path.

| Target | Generation | Bytes | SHA-256 on all four boots | Witness report SHA-256 |
| --- | --- | ---: | --- | --- |
| 1.19.2 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `543d6997e6b3eb3cad9600b76524c9d29d10aac69853e7e05df954266fd6b719` |
| 1.19.4 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `9f27282d710e6d4b2dfd041cb54f327f18d11a9726a56f98f67b4793ecca64f2` |
| 1.20 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `71abf9b8794331598756e4524fb2a34fcac1a802609d657ff5fc46e6fa9fd175` |
| 1.20.1 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `ceffab7cc7c57512ead4ac4a14aeab172d99b8a6042fe896007489bf9624bd70` |
| 1.20.2 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `ca2ffe93f108d93b69b143c75a6ca32a070e27e4e596cd17000f3455f144bfa2` |
| 1.20.3 | Inherited seed | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `27337704daf8f3c073ccb5df5200bb6320bd32e3159b8aca8aed18422bc4fed0` |
| 1.20.4 | Independent boot | 1,300 | `643848724049664a382fbc2ec9607b35f15cbc07222b5164a90da9a0e67cf4b5` | `b15a9923d1d6bb6568c705c32f0d4c9f9dcfd996db1c0d55174273f6a754c329` |
| 1.21.0 | Independent boot | 1,298 | `15f15e656dae244aec2da9dce89669b00665cdcf62d623b1cc187ef56d89e99f` | `f0c3089194e8a32d2aae4dce081add6f87802618e8d23c5e9e04f12c812b5b90` |
| 1.21.1 | Independent boot | 1,361 | `799bb1944ee44357c88012b803298896fb359569c77cda9d06fb4445dd8a1406` | `3596731124153e3768623c9453bc97b563e82abcc7e60da88da6d9d1be2934f5` |
| 26.1.2 | Independent boot | 1,361 | `799bb1944ee44357c88012b803298896fb359569c77cda9d06fb4445dd8a1406` | `947b6591d17708fe4ba764b02202f20f5876e390395ca4517172d7315abe4907` |

The [server witness runner](../../scripts/release-server-witness/Run-ReleaseServerWitness.ps1) copies the official seed world into both comparison boots. Through 1.20.3, that world contains `serverconfig/sfm-server.toml`; those six pairs therefore show that neither boot changed inherited bytes. From 1.20.4 onward, the file is in each boot root's `config` directory, which the runner does not copy. Those four pairs show independent generation of the same bytes. No SFM common config TOML was present in the inspected server witness roots. No client-private settings or user saves were inspected, and no server was launched for this comparison.

This check covers one generated SFM server config per target. It does not prove that every setting was exercised, that arbitrary user edits migrate, or that other config categories match.
