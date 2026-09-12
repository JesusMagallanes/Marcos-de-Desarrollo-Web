"""Cliente minimo del protocolo DevTools de Chrome, solo con la libreria estandar.

No se instala nada: ni Playwright ni Puppeteer ni un paquete nuevo en el
repositorio. Chrome ya esta en la maquina y habla CDP por WebSocket, asi que lo
unico que falta es un cliente de WebSocket, que son unas cuantas lineas de
socket y struct.
"""

import base64
import json
import os
import socket
import struct
import subprocess
import time
import urllib.request


class Ws:
    """Lo justo de RFC 6455 para hablar CDP: texto, enmascarado, sin extensiones."""

    def __init__(self, url):
        resto = url[len("ws://"):]
        host_puerto, _, camino = resto.partition("/")
        host, _, puerto = host_puerto.partition(":")
        self.sock = socket.create_connection((host, int(puerto or 80)))
        self.sock.settimeout(30)
        clave = base64.b64encode(os.urandom(16)).decode()
        pedido = (
            "GET /%s HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\n"
            "Connection: Upgrade\r\nSec-WebSocket-Key: %s\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n" % (camino, host_puerto, clave)
        )
        self.sock.sendall(pedido.encode())
        self.buffer = b""
        while b"\r\n\r\n" not in self.buffer:
            self.buffer += self.sock.recv(4096)
        cabecera, _, self.buffer = self.buffer.partition(b"\r\n\r\n")
        if b"101" not in cabecera.split(b"\r\n")[0]:
            raise RuntimeError("Chrome no acepto el WebSocket: %s" % cabecera[:120])

    def _leer(self, cuantos):
        while len(self.buffer) < cuantos:
            trozo = self.sock.recv(65536)
            if not trozo:
                raise RuntimeError("Chrome cerro la conexion")
            self.buffer += trozo
        salida, self.buffer = self.buffer[:cuantos], self.buffer[cuantos:]
        return salida

    def enviar(self, texto):
        datos = texto.encode()
        cabecera = b"\x81"
        n = len(datos)
        if n < 126:
            cabecera += struct.pack("!B", n | 0x80)
        elif n < 65536:
            cabecera += struct.pack("!BH", 126 | 0x80, n)
        else:
            cabecera += struct.pack("!BQ", 127 | 0x80, n)
        mascara = os.urandom(4)
        cuerpo = bytes(b ^ mascara[i % 4] for i, b in enumerate(datos))
        self.sock.sendall(cabecera + mascara + cuerpo)

    def recibir(self):
        while True:
            b1, b2 = struct.unpack("!BB", self._leer(2))
            opcode = b1 & 0x0F
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack("!H", self._leer(2))[0]
            elif n == 127:
                n = struct.unpack("!Q", self._leer(8))[0]
            carga = self._leer(n)
            if opcode == 0x9:              # ping -> pong
                self.sock.sendall(b"\x8a\x80" + os.urandom(4))
                continue
            if opcode in (0x1, 0x2):
                return carga.decode("utf-8", "replace")
            if opcode == 0x8:
                raise RuntimeError("Chrome cerro la sesion")


class Navegador:

    def __init__(self, perfil, puerto=9222, ancho=1280, alto=2400):
        self.proceso = subprocess.Popen([
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            "--headless=new",
            "--remote-debugging-port=%d" % puerto,
            "--user-data-dir=%s" % perfil,
            "--window-size=%d,%d" % (ancho, alto),
            "--no-first-run", "--no-default-browser-check",
            "--disable-gpu", "--disable-dev-shm-usage",
            "about:blank",
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        destino = None
        for _ in range(60):
            try:
                with urllib.request.urlopen(
                        "http://127.0.0.1:%d/json/list" % puerto, timeout=2) as r:
                    for t in json.load(r):
                        if t.get("type") == "page":
                            destino = t["webSocketDebuggerUrl"]
                            break
            except Exception:
                pass
            if destino:
                break
            time.sleep(0.5)
        if not destino:
            raise RuntimeError("Chrome no levanto el puerto de depuracion")

        self.ws = Ws(destino)
        self.id = 0
        self.manda("Page.enable")
        self.manda("Runtime.enable")

    def manda(self, metodo, **params):
        self.id += 1
        self.ws.enviar(json.dumps({"id": self.id, "method": metodo, "params": params}))
        while True:
            mensaje = json.loads(self.ws.recibir())
            if mensaje.get("id") == self.id:
                if "error" in mensaje:
                    raise RuntimeError("%s -> %s" % (metodo, mensaje["error"]))
                return mensaje.get("result", {})

    def ir(self, url):
        self.manda("Page.navigate", url=url)

    def evaluar(self, expresion):
        salida = self.manda(
            "Runtime.evaluate", expression=expresion, awaitPromise=True,
            returnByValue=True)
        resultado = salida.get("result", {})
        if salida.get("exceptionDetails"):
            raise RuntimeError("JS: %s" % salida["exceptionDetails"].get("text"))
        return resultado.get("value")

    def esperar(self, expresion, segundos=30):
        limite = time.time() + segundos
        while time.time() < limite:
            try:
                if self.evaluar(expresion):
                    return True
            except RuntimeError:
                pass
            time.sleep(0.4)
        return False

    def cerrar(self):
        """Cierra SOLO el navegador que abrio esta instancia.

        Por orden de preferencia: se le pide al propio Chrome que se cierre por
        el protocolo —asi se lleva con el sus procesos hijos—, y si no responde
        se termina el proceso que lanzamos nosotros, por su PID.

        Nunca se mata Chrome por nombre de imagen. Un `taskkill /IM chrome.exe`
        cerraria tambien el navegador personal de quien esta ejecutando esto,
        con sus pestanas y su trabajo dentro. La limpieza de una prueba no puede
        alcanzar a nada que la prueba no haya creado.
        """
        try:
            self.manda("Browser.close")
        except Exception:
            pass
        try:
            self.ws.sock.close()
        except Exception:
            pass
        try:
            self.proceso.wait(timeout=10)
            return
        except Exception:
            pass
        for cerrar in (self.proceso.terminate, self.proceso.kill):
            try:
                cerrar()
                self.proceso.wait(timeout=5)
                return
            except Exception:
                continue
