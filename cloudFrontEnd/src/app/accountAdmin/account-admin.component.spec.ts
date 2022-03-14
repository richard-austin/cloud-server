import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountAdminComponent } from './account-admin.component';

describe('NVRAdminComponent', () => {
  let component: AccountAdminComponent;
  let fixture: ComponentFixture<AccountAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AccountAdminComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
