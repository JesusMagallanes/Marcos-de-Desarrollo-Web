import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ChatbotService } from './chatbot.service';

describe('ChatbotService', () => {
  let service: ChatbotService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ChatbotService],
    });
    service = TestBed.inject(ChatbotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('enviar() llama a POST /api/chatbot/mensaje', () => {
    service.enviar('¿Tienen laptops?').subscribe();
    const req = httpMock.expectOne('/api/chatbot/mensaje');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mensaje: '¿Tienen laptops?', historial: [] });
    req.flush({ respuesta: 'Sí, tenemos laptops disponibles.' });
  });

  it('enviar() trimea el mensaje', () => {
    service.enviar('  Hola  ').subscribe();
    const req = httpMock.expectOne('/api/chatbot/mensaje');
    expect(req.request.body.mensaje).toBe('Hola');
    req.flush({ respuesta: '¡Hola!' });
  });

  it('enviar() manda como mucho 8 turnos de historial, recortados y sin vacíos', () => {
    const historial = Array.from({ length: 10 }, (_, i) => ({
      autor: (i % 2 ? 'asistente' : 'usuario') as 'usuario' | 'asistente',
      texto: i === 9 ? '   ' : 'turno ' + i + ' ' + 'x'.repeat(900),
    }));

    service.enviar('¿y en 16 GB?', historial).subscribe();
    const req = httpMock.expectOne('/api/chatbot/mensaje');

    expect(req.request.body.historial).toHaveLength(8);
    expect(req.request.body.historial[0].texto.startsWith('turno 1 ')).toBe(true);
    expect(req.request.body.historial.every((t: { texto: string }) => t.texto.length <= 800)).toBe(true);
    req.flush({ respuesta: 'ok' });
  });
});
