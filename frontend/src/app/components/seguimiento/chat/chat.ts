import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

type ChatAuthor = 'mecanico' | 'usuario';

interface ChatMessage {
  id: number;
  author: ChatAuthor;
  text: string;
  at: string;
  read: boolean;
}

@Component({
  selector: 'app-seguimiento-chat',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css'
})
export class SeguimientoChatComponent {
  userOnline = false;
  draft = '';

  messages: ChatMessage[] = [
    { id: 1, author: 'mecanico', text: 'He revisado los frenos. Necesitamos cambiar pastillas delanteras.', at: '09:20', read: true },
    { id: 2, author: 'usuario', text: 'Perfecto, adelante con el cambio.', at: '09:25', read: true },
    { id: 3, author: 'mecanico', text: 'Trabajo terminado. Quedo atento para entrega.', at: '10:02', read: false }
  ];

  get unreadCount(): number {
    return this.messages.filter((msg) => msg.author === 'mecanico' && !msg.read).length;
  }

  sendMessage(): void {
    const value = this.draft.trim();
    if (!value) {
      return;
    }

    this.messages = [
      ...this.messages,
      {
        id: this.messages.length + 1,
        author: 'usuario',
        text: value,
        at: new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }),
        read: true
      }
    ];

    this.draft = '';
  }
}
