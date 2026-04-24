import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-seguimiento-page',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './seguimiento.html',
  styleUrl: './seguimiento.css'
})
export class SeguimientoComponent {
  readonly userOnline = false;
  readonly unreadCount = 2;
}
