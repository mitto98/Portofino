import {AuthenticationStrategy, TOKEN_STORAGE_SERVICE} from "../authentication.service";
import {Inject, Injectable, InjectionToken} from "@angular/core";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {Observable} from "rxjs";
import {NO_REFRESH_TOKEN_HEADER} from "../authentication.headers";
import moment from "moment-with-locales-es6";
import {HttpClient, HttpHeaders, HttpRequest, HttpResponse} from "@angular/common/http";
import {PortofinoService} from "../../portofino.service";
import {NotificationService} from "../../notifications/notification.services";
import {TranslateService} from "@ngx-translate/core";
import {map} from "rxjs/operators";
import {LocalStorageService, WebStorageService} from "../../storage/storage.services";

export const LOGIN_COMPONENT = new InjectionToken('Login Component');
export const CHANGE_PASSWORD_COMPONENT = new InjectionToken('Change Password Component');
export const RESET_PASSWORD_COMPONENT = new InjectionToken('Reset Password Component');

@Injectable()
export class InAppAuthenticationStrategy extends AuthenticationStrategy {
  constructor(
    protected portofino: PortofinoService, protected http: HttpClient,
    protected notifications: NotificationService, protected translate: TranslateService,
    protected dialog: MatDialog,
    @Inject(LOGIN_COMPONENT) protected loginComponent,
    @Inject(CHANGE_PASSWORD_COMPONENT) protected changePasswordComponent,
    @Inject(RESET_PASSWORD_COMPONENT) protected resetPasswordComponent) {
    super();
  }

  askForCredentials(): Observable<any> {
    const dialogRef = this.dialog.open(this.loginComponent);
    return dialogRef.afterClosed();
  }

  goToChangePassword(): MatDialogRef<unknown> {
    return this.dialog.open(this.changePasswordComponent);
  }

  goToResetPassword(token: string): MatDialogRef<unknown> {
    return this.dialog.open(this.resetPasswordComponent, {
      data: { token: token }
    });
  }

  refreshToken(): Observable<string> {
    //The body here is to work around CORS requests failing with an empty body (TODO investigate)
    return this.authentication.withAuthenticationHeader(
      new HttpRequest<any>("POST", `${this.loginPath}/:refresh-token`, "renew", {
        headers: new HttpHeaders().set(NO_REFRESH_TOKEN_HEADER, 'true'), responseType: 'text'
      })).pipe(map(
      event => {
        if (event instanceof HttpResponse) {
          if (event.status == 200) {
            return event.body;
          } else {
            throw "Failed to refresh access token";
          }
        }
      }));
  }

  logout(): Observable<any> {
    const url = `${this.loginPath}`;
    return this.http.delete(url, {
      headers: new HttpHeaders().set(NO_REFRESH_TOKEN_HEADER, 'true')
    });
  }

  confirmSignup(token: string) {
    this.http.post(`${this.loginPath}/user/:confirm`,{ token: token });
  }

  get loginPath() {
    return `${this.portofino.apiRoot}${this.portofino.loginPath}`;
  }


}
