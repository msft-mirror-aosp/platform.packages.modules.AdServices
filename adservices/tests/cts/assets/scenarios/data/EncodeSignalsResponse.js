function encodeSignals(signals, maxSize) {
   return {'status' : 0, 'results' : new Uint8Array([signals.length])};
}