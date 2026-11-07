; Astro Launcher installer additions (electron-builder NSIS include).
; Adds a setup page offering the "Uninstall Astro Launcher" shortcut, which opens Astro's own
; uninstall window (Astro Launcher.exe --uninstall) instead of the plain Windows uninstaller.

!include MUI2.nsh
!include nsDialogs.nsh
!include LogicLib.nsh

!ifndef BUILD_UNINSTALLER
  Var AstroUninstCheckbox
  Var AstroUninstShortcut

  !macro customPageAfterChangeDir
    Page custom AstroUninstPageCreate AstroUninstPageLeave
  !macroend

  Function AstroUninstPageCreate
    !insertmacro MUI_HEADER_TEXT "Astro Uninstaller" "Choose how you want to uninstall Astro later."
    nsDialogs::Create 1018
    Pop $0
    ${If} $0 == error
      Abort
    ${EndIf}

    ${NSD_CreateLabel} 0 0 100% 40u "Astro comes with its own uninstaller. It lets you pick what to remove (profiles and worlds, downloaded game files, Java, settings) and keeps the rest."
    Pop $0
    ${NSD_CreateCheckbox} 0 48u 100% 12u "Add an $\"Uninstall Astro Launcher$\" shortcut to the Start menu and desktop"
    Pop $AstroUninstCheckbox
    ${NSD_Check} $AstroUninstCheckbox
    ${NSD_CreateLabel} 0 68u 100% 24u "You can also uninstall from Windows Settings > Apps at any time."
    Pop $0
    nsDialogs::Show
  FunctionEnd

  Function AstroUninstPageLeave
    ${NSD_GetState} $AstroUninstCheckbox $AstroUninstShortcut
  FunctionEnd
!endif

!macro customInstall
  ${If} $AstroUninstShortcut == ${BST_CHECKED}
    CreateShortCut "$SMPROGRAMS\Uninstall Astro Launcher.lnk" "$INSTDIR\Astro Launcher.exe" "--uninstall" "$INSTDIR\Astro Launcher.exe" 0
    CreateShortCut "$DESKTOP\Uninstall Astro Launcher.lnk" "$INSTDIR\Astro Launcher.exe" "--uninstall" "$INSTDIR\Astro Launcher.exe" 0
  ${EndIf}
!macroend

!macro customUnInstall
  Delete "$SMPROGRAMS\Uninstall Astro Launcher.lnk"
  Delete "$DESKTOP\Uninstall Astro Launcher.lnk"
!macroend
