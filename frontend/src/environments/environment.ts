export const environment = {
  production: false,
  // En desarrollo las llamadas salen relativas y proxy.conf.json las manda al
  // gateway (8080). Así no hay CORS ni puertos incrustados en el código.
  apiUrl: '',
  mercadoPagoPublicKey: '',
};
