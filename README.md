# InvisionWorld 1.6.0

Collective chunk expansion for the InVision anarchy server.

## What changed
- Initial playable area: 16x16 chunks (256x256 blocks) in the chunk containing the configured Multiverse spawn.
- A purchase opens exactly one additional chunk; the economy/fund is shared by everyone.
- The native Minecraft WorldBorder is **not** used as the dynamic chunk boundary because it is always a square. Real access is enforced per chunk.
- Near the actual frontier, players see a subtle red boundary preview; currently purchasable chunks nearby get a subtle yellow outline. Effects are local and only appear within the configured radius.
- The map stays fixed and the player marker moves by chunk.
- The same expansion shape is applied to the configured Nether and End worlds. The initial 16x16 area is open there immediately; later purchases unlock the same relative chunk in those dimensions.
- Database remains SQLite in `plugins/InvisionWorld/data.db`. Existing purchases are preserved.

## Build
```bash
gradle clean shadowJar --no-daemon
```
Output: `build/libs/InvisionWorld-1.6.0.jar`

## Protected worlds
Default config: `anarchy`, `anarchy_nether`, `anarchy_the_end`. Change `protected-worlds` in `config.yml` to match your Multiverse world names.
