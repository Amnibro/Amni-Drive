# Guardian council — v1.5.6 post-TLS SD role fix
## Architect
Phone must speak MD role: ServiceDiscoveryRequest (fields 4/5), not HU ServiceDiscoveryResponse. Wrong role explains 0xff after auth+SD.
## Sentinel
Do not claim M5. Keep exclusive session. Mesh token chicken-egg fixed via public /token on mesh LAN only.
## Scholar
aasdk protos: SD Request device_name=4 device_brand=5; SD Response channels=1 ChannelDescriptor; ChannelOpen priority=1 channel_id=2; Auth status OK=0.
## Engineer
Ship Request + parse Response + open AV/input/sensor channels; ENC decrypt uses full frame payload; dual TX enc-frame+tunnel.
## Pathfinder
Name variants AmniDrive/Android Auto/Galaxy/Pixel on 0xff retry. Install 1.5.6, physical replug, claim OWN.
## Vote
4–1 ship role fix now (Architect/Scholar/Engineer/Pathfinder). Sentinel abstain on M5 only.
