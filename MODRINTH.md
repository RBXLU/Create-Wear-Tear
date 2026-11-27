# Create: Wear & Tear

**Your machines are no longer immortal.** Cogwheels, shafts, gearboxes and machines slowly wear out while they turn. Run them too fast, overload the network or slam them into reverse and they break even sooner. Watch for the scratches and cracks, replace parts before they fail, and harden the important ones with a compound brewed in your own basin.

An addon for **Create 6** on **NeoForge 1.21.1**.

---

## ⚙️ Parts wear out
Every rotating part slowly wears down while it turns. **Cogwheels go first**: a small one lasts about 2.5 hours of play at 64 RPM. Shafts, gearboxes and machines last longer. Every part rolls its own build quality, so a factory never fails all at once.

You can see the wear on the part itself:

- **Scratched:** bright scratches, gouges and rubbed corners
- **Badly worn:** grime, first cracks, and the first gear teeth snap off
- **Broken:** deep cracks, chips, and only stubs left of the teeth

Each part picks one of several wear patterns, so neighbouring gears crack differently, and the plates of a crushing wheel all wear in different places.

## 🦷 Teeth snap off
When a gear wears past a stage, its teeth break off one by one. **Each tooth flies off along the direction of rotation as a real little wooden block**, tumbles, bounces on the floor and disappears. On the gear, a stub remains where the tooth was.

## 💥 Misuse breaks things
- **Overspeed:** every part has a rated speed. Push past it and wear climbs steeply.
- **Gear ratio strain:** driven gears in a big step-up or step-down carry more load.
- **Overload:** networks running above 75% of their stress capacity grind their parts down.
- **Jolts:** reversing a loaded line or jumping speeds hits the teeth hard, and you hear it.
- **Corrosion:** waterlogged parts rust faster.
- **Slipping crushing wheels:** run a pair at 20 and 50 RPM and their plates grind perfectly round in minutes. A smooth wheel still spins but crushes nothing.

## ⚡ Sudden failures
Parts don't always wait until they are worn out. **Any running part can suddenly give way.** A new one rarely does, but a badly worn, overspeeding or overloaded part is far more likely to. Hardened parts fail three times less often, and parts that are not turning never do.

You get a warning: the part **creaks, sparks and smokes for a few seconds**, and your goggles show a countdown. **Stop the network in time and the part survives** with a little extra wear. Miss it, and the part breaks with a bang that jolts every part coupled to it.

## 🔧 How parts fail
- **Gears and chain drives** strip their teeth. They keep turning with their own shaft but no longer drive anything meshed with them.
- **Shafts, gearboxes and machines** seize and stop passing rotation on.
- **Crushing wheels** are ground smooth and stop crushing.

Broken parts have to be replaced, and mining one gives **Mechanical Scrap** that you can crush back into a little material. Worn parts keep their wear as an item, so mining and placing them again (or encasing and un-encasing them) does not reset anything.

Each failure has its own sound: splintering wood, a ringing metal clank or a long stony scrape. All sounds are custom-made and have subtitles.

## 🥽 Engineer's Goggles
Look at any part through goggles to see its **condition, what is wearing it down faster and roughly how long it has left**. Broken parts also warn you without goggles.

## 🧪 Hardening Compound
1. Pour **1000 mB of water** into a basin and add **andesite alloy, a gold ingot and an iron ingot**.
2. Mix with a **Mechanical Mixer** (no heat needed).
3. Collect the compound with a **bucket**.
4. **Right-click any placed part** with the bucket to harden it.

Hardened parts wear about **three times slower**, shrug off half of every jolt and carry small golden traces of the coating. For automation, a **Spout** coats brand-new part items with 250 mB each.

## 🛠️ For pack makers
- Server config for every rate, penalty and failure behaviour, including how often sudden failures happen and how long the warning lasts.
- Which blocks wear is a **data map** (`create_wear:wear_profiles`), so datapacks can tune lifetimes or add parts from other addons:
  ```json
  { "values": { "create:cogwheel": { "lifetime": 150, "rated_speed": 128, "failure": "teeth" } } }
  ```
- `/createwear get|set|repair|reinforce|fail <pos>` for admins and map makers.
- English and Russian translations.

## ❓ FAQ
**Does it ship Create's textures?** No. The worn looks are generated in memory from your own Create install when resources load. The mod itself contains only original art and sounds.

**Does it work with Flywheel?** Yes. Worn models are used by Flywheel's instanced rendering and by Create's fallback block entity renderer (the one used when Flywheel's backend is off).

**Is it required on both sides?** Yes, install it on the server and on every client.
