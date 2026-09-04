import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { environment } from '../environments/environment';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('DevAtlas');

  /**
   * Which client this bundle was built for, read from the build-time
   * environment. Reading it here keeps the `tauri` configuration's file
   * replacement observable in the emitted bundle: without a live reference the
   * environment module is tree-shaken away and both targets would produce
   * byte-identical output, so a broken replacement would go unnoticed.
   *
   * Feature code must not branch on this value. It selects the platform
   * service implementation during bootstrap, and components stay unaware of
   * which implementation they were given.
   */
  protected readonly platform = signal(environment.platform);
}
