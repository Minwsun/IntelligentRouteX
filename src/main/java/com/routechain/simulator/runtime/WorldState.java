package com.routechain.simulator.runtime;

import com.routechain.simulator.demand.SimOrder;
import com.routechain.simulator.driver.SimDriver;
import com.routechain.simulator.merchant.SimMerchant;
import com.routechain.simulator.traffic.TrafficSnapshot;
import com.routechain.simulator.weather.WeatherSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class WorldState {
    private final int worldIndex;
    private Instant worldTime;
    private int tickIndex;
    private final List<SimMerchant> merchants;
    private final List<SimDriver> drivers;
    private final List<SimOrder> orders;
    private WeatherSnapshot weatherSnapshot;
    private TrafficSnapshot trafficSnapshot;

    public WorldState(int worldIndex,
                      Instant worldTime,
                      List<SimMerchant> merchants,
                      List<SimDriver> drivers) {
        this.worldIndex = worldIndex;
        this.worldTime = worldTime;
        this.merchants = new ArrayList<>(merchants);
        this.drivers = new ArrayList<>(drivers);
        this.orders = new ArrayList<>();
    }

    public int worldIndex() {
        return worldIndex;
    }

    public Instant worldTime() {
        return worldTime;
    }

    public void worldTime(Instant worldTime) {
        this.worldTime = worldTime;
    }

    public int tickIndex() {
        return tickIndex;
    }

    public void tickIndex(int tickIndex) {
        this.tickIndex = tickIndex;
    }

    public List<SimMerchant> merchants() {
        return merchants;
    }

    public List<SimDriver> drivers() {
        return drivers;
    }

    public List<SimOrder> orders() {
        return orders;
    }

    public void addOrders(List<SimOrder> newOrders) {
        orders.addAll(newOrders);
    }

    public WeatherSnapshot weatherSnapshot() {
        return weatherSnapshot;
    }

    public void weatherSnapshot(WeatherSnapshot weatherSnapshot) {
        this.weatherSnapshot = weatherSnapshot;
    }

    public TrafficSnapshot trafficSnapshot() {
        return trafficSnapshot;
    }

    public void trafficSnapshot(TrafficSnapshot trafficSnapshot) {
        this.trafficSnapshot = trafficSnapshot;
    }
}
