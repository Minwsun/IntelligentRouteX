(function () {
    const defaultCenter = [10.7769, 106.7009];

    window.RouteChainMapApp = {
        createMap(mapId) {
            const map = L.map(mapId).setView(defaultCenter, 13);
            L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
                maxZoom: 19,
                attribution: "&copy; OpenStreetMap contributors"
            }).addTo(map);
            return {
                map,
                layers: {
                    nearby: L.layerGroup().addTo(map),
                    trip: L.layerGroup().addTo(map)
                }
            };
        },

        markerIcon(className) {
            return L.divIcon({
                className,
                iconSize: [16, 16],
                iconAnchor: [8, 8]
            });
        },

        clearLayer(layer) {
            if (layer) {
                layer.clearLayers();
            }
        },

        drawSnapshot(state, snapshot) {
            if (!snapshot) {
                return;
            }
            this.clearLayer(state.layers.nearby);
            this.clearLayer(state.layers.trip);

            (snapshot.nearbyDrivers || []).forEach(driver => {
                L.marker([driver.lat, driver.lng], { icon: this.markerIcon("driver-marker") })
                    .bindPopup(`${driver.driverId}<br>${driver.state}<br>${driver.distanceKm.toFixed(2)} km`)
                    .addTo(state.layers.nearby);
            });

            if (snapshot.pickup) {
                L.marker([snapshot.pickup.lat, snapshot.pickup.lng], { icon: this.markerIcon("poi-marker") })
                    .bindPopup("Pickup")
                    .addTo(state.layers.trip);
            }
            if (snapshot.dropoff) {
                L.marker([snapshot.dropoff.lat, snapshot.dropoff.lng], { icon: this.markerIcon("poi-marker") })
                    .bindPopup("Dropoff")
                    .addTo(state.layers.trip);
            }
            if (snapshot.assignedDriver) {
                L.marker([snapshot.assignedDriver.lat, snapshot.assignedDriver.lng], { icon: this.markerIcon("rider-marker") })
                    .bindPopup(`${snapshot.assignedDriver.driverId}<br>${snapshot.status}`)
                    .addTo(state.layers.trip);
            }
            const polyline = snapshot.routePolyline || [];
            if (polyline.length >= 2) {
                L.polyline(polyline.map(point => [point.lat, point.lng]), {
                    color: "#164e3c",
                    weight: 4,
                    opacity: 0.82
                }).addTo(state.layers.trip);
                state.map.fitBounds(polyline.map(point => [point.lat, point.lng]), { padding: [30, 30] });
            }
        },

        connectSocket(path, onMessage) {
            const socketProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
            const socket = new WebSocket(`${socketProtocol}//${window.location.host}${path}`);
            socket.onmessage = event => {
                try {
                    onMessage(JSON.parse(event.data));
                } catch (error) {
                    console.error("Unable to parse websocket payload", error);
                }
            };
            return socket;
        },

        async fetchJson(url, options) {
            const response = await fetch(url, options);
            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || `Request failed: ${response.status}`);
            }
            return response.json();
        }
    };
})();
