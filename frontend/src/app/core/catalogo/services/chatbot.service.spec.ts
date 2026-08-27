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
    expect(req.request.body).toEqual({ mensaje: '¿Tienen laptops?' });
    req.flush({ respuesta: 'Sí, tenemos laptops disponibles.' });
  });

  it('enviar() trimea el mensaje', () => {
    service.enviar('  Hola  ').subscribe();
    const req = httpMock.expectOne('/api/chatbot/mensaje');
    expect(req.request.body.mensaje).toBe('Hola');
    req.flush({ respuesta: '¡Hola!' });
  });
});
