#!/usr/bin/env bash
./kind create cluster --name kind-2
kubectl cluster-info --context kind-test
kubectl apply -f pgadmin.yaml