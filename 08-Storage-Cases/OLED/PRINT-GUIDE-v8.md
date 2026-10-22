# Scout OLED case v8: thin shell with 5 mm bevel

The OLED revision returns the closed shell to its original nominal 107.6 x 155.6 x 63.6 mm envelope, excluding hinge projections. Broad walls, floor and roof are 2.8 mm before the shallow decorative recesses. The 5 mm 45-degree outside bevel has 1 mm softened transitions. A matching inside bevel (3.3598 mm setback) lets the inside corners follow the exterior, rather than filling the shell to v7's 7.8 mm flat thickness.

The original front wall is retained locally across the latch mounting region (Y -1 to 33 mm), and the original captive hinge roots are retained. These functional lands interrupt the bevel locally so it does not weaken the latch attachment or hinge. Original shallow graphics and grip recesses remain. The minimum wall in a shallow decorative recess can be below the nominal 2.8 mm, as in v6.

The four original Scout support positions and heights remain unchanged. Side guides are 5 mm taller than v6, and the two rounded rear locators from v7 flank the tail. The 0.20 mm-per-face vertical dovetail, separate flat spring and front push-release mechanism retain the v6 geometry. The separate latch is the OLED length.

## Files and printing

Use Scout-OLED-Case-v8.f3d for Fusion editing. The complete STEP/STL has the captive box pair and separate flat latch. The -Box.stl and -Latch.stl are separate alternatives. Do not load complete and separate meshes together, and never independently arrange the base and lid.

Use the v8 P1S Studio project and fresh slice. Older v6/v7 sliced plates are not valid for v8. The supplied orientation puts the captive pair on the bed with its intended hinge relationship and lays the spring on its broad face. Setup: P1S 0.4 mm, PLA Basic filament 1, textured PEI, 0.20 mm layers, four walls, 15% grid, five top and three bottom layers, no supports/brim/prime tower. Use the same calibrated material as the successful fit samples.

## Assembly and limits

Cool the print before gently freeing the captive hinges. With the lid open, align the thick dovetail foot at the internal entry channel and slide it toward the lid roof until seated. Hold the thick foot rather than levering the flexible blade. Close gently for the first test. Push the catch inward through the front opening, lift the lid through the first few degrees and release.

The delivered geometry is checked against the full 700-body assembled OLED Scout reference, through latch insertion and sampled opening motion. Rigid checks do not simulate spring bending, printed dimensional error or fatigue. Check actual wiring and straps and gently cycle the printed latch before loading Scout. Earlier v6/v7 files are preserved. User parameters do not drive every inherited feature; the native Fusion archive contains editable feature and BRep history.

## Verified Studio result

Estimated 4h 11m 48s, 192.72 g PLA, 189 layers. This is about 37% less filament than the saved v7 slice (306.4 g). Complete plate bounds are X15.6â€“240.4, Y35.1â€“220.9 mm, with no exclusion-area error. Critical layers were inspected at 0.4, 5.0, 10.2 and 27.6 mm, then restored to full height. Geometry and sliced checksum checks passed. No job was sent to a printer.

Unused inherited filament profiles may show a zero-flushing-volume warning. Only PLA filament 1 prints; confirm the actual loaded spool before starting. Independent native sampling confirmed 2.8 mm at the broad floor, roof, side and end wall. The complete mesh and separate box/latch meshes passed closed-manifold, volume and dimensional checks.
